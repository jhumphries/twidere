/*
 *				Twidere - Twitter client for Android
 * 
 * Copyright (C) 2012 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.fragment;

import static org.mariotaku.twidere.util.Utils.cancelRetweet;
import static org.mariotaku.twidere.util.Utils.getActivatedAccountIds;
import static org.mariotaku.twidere.util.Utils.getQuoteStatus;
import static org.mariotaku.twidere.util.Utils.isMyRetweet;
import static org.mariotaku.twidere.util.Utils.openStatus;
import static org.mariotaku.twidere.util.Utils.setMenuForStatus;

import java.util.List;

import org.mariotaku.popupmenu.PopupMenu;
import org.mariotaku.popupmenu.PopupMenu.OnMenuItemClickListener;
import org.mariotaku.twidere.R;
import org.mariotaku.twidere.app.TwidereApplication;
import org.mariotaku.twidere.model.Panes;
import org.mariotaku.twidere.model.ParcelableStatus;
import org.mariotaku.twidere.model.StatusViewHolder;
import org.mariotaku.twidere.util.AsyncTaskManager;
import org.mariotaku.twidere.util.ClipboardUtils;
import org.mariotaku.twidere.util.NoDuplicatesLinkedList;
import org.mariotaku.twidere.util.TwitterWrapper;
import org.mariotaku.twidere.util.StatusesAdapterInterface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.handmark.pulltorefresh.library.PullToRefreshBase.Mode;
import com.twitter.Extractor;

abstract class BaseStatusesListFragment<Data> extends PullToRefreshListFragment implements LoaderCallbacks<Data>,
		OnScrollListener, OnItemLongClickListener, OnMenuItemClickListener, Panes.Left {

	private static final long TICKER_DURATION = 5000L;

	private TwitterWrapper mTwitterWrapper;
	private TwidereApplication mApplication;
	private SharedPreferences mPreferences;
	private AsyncTaskManager mAsyncTaskManager;

	private Handler mHandler;
	private Runnable mTicker;

	private ListView mListView;

	private StatusesAdapterInterface mAdapter;
	protected PopupMenu mPopupMenu;

	protected Data mData;
	protected ParcelableStatus mSelectedStatus;

	private boolean mLoadMoreAutomatically;

	private volatile boolean mBusy, mTickerStopped, mReachedBottom, mNotReachedBottomBefore = true;

	private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (BROADCAST_MULTI_SELECT_STATE_CHANGED.equals(action)) {
				mAdapter.setMultiSelectEnabled(mApplication.isMultiSelectActive());
			} else if (BROADCAST_MULTI_SELECT_ITEM_CHANGED.equals(action)) {
				mAdapter.notifyDataSetChanged();
			}
		}

	};

	public AsyncTaskManager getAsyncTaskManager() {
		return mAsyncTaskManager;
	}

	public final Data getData() {
		return mData;
	}

	@Override
	public abstract StatusesAdapterInterface getListAdapter();

	public ParcelableStatus getSelectedStatus() {
		return mSelectedStatus;
	}

	public SharedPreferences getSharedPreferences() {
		return mPreferences;
	}

	public abstract int getStatuses(long[] account_ids, long[] max_ids, long[] since_ids);

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mApplication = getApplication();
		mAsyncTaskManager = getAsyncTaskManager();
		mPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
		mTwitterWrapper = getTwitterWrapper();
		mListView = getListView();
		mAdapter = getListAdapter();
		setListAdapter(null);
		setListHeaderFooters(mListView);
		setListAdapter(mAdapter);
		mListView.setOnScrollListener(this);
		mListView.setOnItemLongClickListener(this);
		setMode(Mode.BOTH);
		getLoaderManager().initLoader(0, getArguments(), this);
		setListShown(false);
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Tell the framework to try to keep this fragment around
		// during a configuration change.
		setRetainInstance(true);
	}

	@Override
	public abstract Loader<Data> onCreateLoader(int id, Bundle args);

	@Override
	public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
		mSelectedStatus = null;
		final Object tag = view.getTag();
		if (tag instanceof StatusViewHolder) {
			final boolean click_to_open_menu = mPreferences.getBoolean(PREFERENCE_KEY_CLICK_TO_OPEN_MENU, false);
			final StatusViewHolder holder = (StatusViewHolder) tag;
			if (holder.show_as_gap) return false;
			final ParcelableStatus status = mSelectedStatus = mAdapter.getStatus(position
					- mListView.getHeaderViewsCount());
			if (mApplication.isMultiSelectActive()) {
				final NoDuplicatesLinkedList<Object> list = mApplication.getSelectedItems();
				if (!list.contains(mSelectedStatus)) {
					list.add(mSelectedStatus);
				} else {
					list.remove(mSelectedStatus);
				}
				return true;
			}
			if (click_to_open_menu) {
				if (!mApplication.isMultiSelectActive()) {
					mApplication.startMultiSelect();
				}
				final NoDuplicatesLinkedList<Object> list = mApplication.getSelectedItems();
				if (!list.contains(status)) {
					list.add(status);
				}
			} else {
				openMenu(view, status);
			}
			return true;
		}
		return false;
	}

	@Override
	public void onListItemClick(final ListView l, final View v, final int position, final long id) {
		mSelectedStatus = null;
		final Object tag = v.getTag();
		if (tag instanceof StatusViewHolder) {
			final boolean click_to_open_menu = mPreferences.getBoolean(PREFERENCE_KEY_CLICK_TO_OPEN_MENU, false);
			final ParcelableStatus status = mSelectedStatus = mAdapter.getStatus(position - l.getHeaderViewsCount());
			if (status == null) return;
			final StatusViewHolder holder = (StatusViewHolder) tag;
			if (holder.show_as_gap) {
				getStatuses(new long[] { status.account_id }, new long[] { status.status_id }, null);
			} else {
				if (mApplication.isMultiSelectActive()) {
					final NoDuplicatesLinkedList<Object> list = mApplication.getSelectedItems();
					if (!list.contains(status)) {
						list.add(status);
					} else {
						list.remove(status);
					}
					return;
				}
				if (click_to_open_menu) {
					openMenu(v, status);
				} else {
					openStatus(getActivity(), status);
				}
			}
		}
	}

	@Override
	public void onLoaderReset(final Loader<Data> loader) {
		mData = null;
	}

	@Override
	public void onLoadFinished(final Loader<Data> loader, final Data data) {
		mData = data;
		mAdapter.setShowAccountColor(getActivatedAccountIds(getActivity()).length > 1);
		setListShown(true);
	}

	@Override
	public boolean onMenuItemClick(final MenuItem item) {
		final ParcelableStatus status = mSelectedStatus;
		if (status == null) return false;
		switch (item.getItemId()) {
			case MENU_VIEW: {
				openStatus(getActivity(), status);
				break;
			}
			case MENU_SHARE: {
				final Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, "@" + status.screen_name + ": " + status.text_plain);
				startActivity(Intent.createChooser(intent, getString(R.string.share)));
				break;
			}
			case MENU_COPY: {
				final CharSequence text = Html.fromHtml(status.text_html);
				ClipboardUtils.setText(getActivity(), text);
				Toast.makeText(getActivity(), R.string.text_copied, Toast.LENGTH_SHORT).show();
				break;
			}
			case R.id.direct_retweet:
			case MENU_RETWEET: {
				if (isMyRetweet(status)) {
					cancelRetweet(mTwitterWrapper, status);
				} else {
					final long id_to_retweet = mSelectedStatus.is_retweet && mSelectedStatus.retweet_id > 0 ? mSelectedStatus.retweet_id
							: mSelectedStatus.status_id;
					mTwitterWrapper.retweetStatus(status.account_id, id_to_retweet);
				}
				break;
			}
			case R.id.direct_quote:
			case MENU_QUOTE: {
				final Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				final Bundle bundle = new Bundle();
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, status.account_id);
				bundle.putLong(INTENT_KEY_IN_REPLY_TO_ID, status.status_id);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME, status.screen_name);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_NAME, status.name);
				bundle.putBoolean(INTENT_KEY_IS_QUOTE, true);
				bundle.putString(INTENT_KEY_TEXT, getQuoteStatus(getActivity(), status.screen_name, status.text_plain));
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_REPLY: {
				final Intent intent = new Intent(INTENT_ACTION_COMPOSE);
				final Bundle bundle = new Bundle();
				final List<String> mentions = new Extractor().extractMentionedScreennames(status.text_plain);
				mentions.remove(status.screen_name);
				mentions.add(0, status.screen_name);
				bundle.putStringArray(INTENT_KEY_MENTIONS, mentions.toArray(new String[mentions.size()]));
				bundle.putLong(INTENT_KEY_ACCOUNT_ID, status.account_id);
				bundle.putLong(INTENT_KEY_IN_REPLY_TO_ID, status.status_id);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_SCREEN_NAME, status.screen_name);
				bundle.putString(INTENT_KEY_IN_REPLY_TO_NAME, status.name);
				intent.putExtras(bundle);
				startActivity(intent);
				break;
			}
			case MENU_FAVORITE: {
				if (mSelectedStatus.is_favorite) {
					mTwitterWrapper.destroyFavorite(status.account_id, status.status_id);
				} else {
					mTwitterWrapper.createFavorite(status.account_id, status.status_id);
				}
				break;
			}
			case MENU_DELETE: {
				mTwitterWrapper.destroyStatus(status.account_id, status.status_id);
				break;
			}
			case MENU_EXTENSIONS: {
				final Intent intent = new Intent(INTENT_ACTION_EXTENSION_OPEN_STATUS);
				final Bundle extras = new Bundle();
				extras.putParcelable(INTENT_KEY_STATUS, status);
				intent.putExtras(extras);
				startActivity(Intent.createChooser(intent, getString(R.string.open_with_extensions)));
				break;
			}
			case MENU_LOAD_FROM_POSITION: {
				getStatuses(new long[] { status.account_id }, new long[] { status.status_id }, null);
				break;
			}
			case MENU_MULTI_SELECT: {
				if (!mApplication.isMultiSelectActive()) {
					mApplication.startMultiSelect();
				}
				final NoDuplicatesLinkedList<Object> list = mApplication.getSelectedItems();
				if (!list.contains(status)) {
					list.add(status);
				}
				break;
			}
		}
		return true;
	}

	@Override
	public abstract void onPullDownToRefresh();

	@Override
	public void onResume() {
		super.onResume();
		mLoadMoreAutomatically = mPreferences.getBoolean(PREFERENCE_KEY_LOAD_MORE_AUTOMATICALLY, false);
		final float text_size = mPreferences.getInt(PREFERENCE_KEY_TEXT_SIZE, PREFERENCE_DEFAULT_TEXT_SIZE);
		final boolean display_profile_image = mPreferences.getBoolean(PREFERENCE_KEY_DISPLAY_PROFILE_IMAGE, true);
		final boolean display_image_preview = mPreferences.getBoolean(PREFERENCE_KEY_INLINE_IMAGE_PREVIEW, false);
		final boolean show_absolute_time = mPreferences.getBoolean(PREFERENCE_KEY_SHOW_ABSOLUTE_TIME, false);
		final String name_display_option = mPreferences.getString(PREFERENCE_KEY_NAME_DISPLAY_OPTION,
				NAME_DISPLAY_OPTION_BOTH);
		mAdapter.setMultiSelectEnabled(mApplication.isMultiSelectActive());
		mAdapter.setDisplayProfileImage(display_profile_image);
		mAdapter.setDisplayImagePreview(display_image_preview);
		mAdapter.setTextSize(text_size);
		mAdapter.setShowAbsoluteTime(show_absolute_time);
		mAdapter.setNameDisplayOption(name_display_option);
	}

	@Override
	public void onScroll(final AbsListView view, final int firstVisibleItem, final int visibleItemCount,
			final int totalItemCount) {
		final boolean reached = firstVisibleItem + visibleItemCount >= totalItemCount
				&& totalItemCount >= visibleItemCount;

		if (mReachedBottom != reached) {
			mReachedBottom = reached;
			if (mReachedBottom && mNotReachedBottomBefore) {
				mNotReachedBottomBefore = false;
				return;
			}
			if (mLoadMoreAutomatically && mReachedBottom && getListAdapter().getCount() > visibleItemCount) {
				if (!isRefreshing()) {
					onPullUpToRefresh();
				}
			}
		}

	}

	@Override
	public void onScrollStateChanged(final AbsListView view, final int scrollState) {
		switch (scrollState) {
			case SCROLL_STATE_FLING:
			case SCROLL_STATE_TOUCH_SCROLL:
				mBusy = true;
				break;
			case SCROLL_STATE_IDLE:
				mBusy = false;
				break;
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		mTickerStopped = false;
		mHandler = new Handler();

		mTicker = new Runnable() {

			@Override
			public void run() {
				if (mTickerStopped) return;
				if (mListView != null && !mBusy) {
					mAdapter.notifyDataSetChanged();
				}
				final long now = SystemClock.uptimeMillis();
				final long next = now + TICKER_DURATION - now % TICKER_DURATION;
				mHandler.postAtTime(mTicker, next);
			}
		};
		mTicker.run();

		final IntentFilter filter = new IntentFilter();
		filter.addAction(BROADCAST_MULTI_SELECT_STATE_CHANGED);
		filter.addAction(BROADCAST_MULTI_SELECT_ITEM_CHANGED);
		registerReceiver(mStateReceiver, filter);

	}

	@Override
	public void onStop() {
		mTickerStopped = true;
		if (mPopupMenu != null) {
			mPopupMenu.dismiss();
		}
		unregisterReceiver(mStateReceiver);
		super.onStop();
	}

	private void openMenu(final View view, final ParcelableStatus status) {
		if (view == null || status == null) return;
		final int activated_color = getResources().getColor(R.color.holo_blue_bright);
		mPopupMenu = PopupMenu.getInstance(getActivity(), view);
		mPopupMenu.inflate(R.menu.action_status);
		final boolean click_to_open_menu = mPreferences.getBoolean(PREFERENCE_KEY_CLICK_TO_OPEN_MENU, false);
		final boolean seprate_retweet_action = mPreferences.getBoolean(PREFERENCE_KEY_SEPRATE_RETWEET_ACTION, false);
		final Menu menu = mPopupMenu.getMenu();
		setMenuForStatus(getActivity(), menu, status);
		final MenuItem view_status = menu.findItem(MENU_VIEW);
		if (view_status != null) {
			view_status.setVisible(click_to_open_menu);
		}
		final MenuItem retweet_submenu = menu.findItem(R.id.retweet_submenu);
		if (retweet_submenu != null) {
			retweet_submenu.setVisible(!seprate_retweet_action);
		}
		final MenuItem direct_quote = menu.findItem(R.id.direct_quote);
		if (direct_quote != null) {
			direct_quote.setVisible(seprate_retweet_action);
		}
		final MenuItem direct_retweet = menu.findItem(R.id.direct_retweet);
		if (direct_retweet != null) {
			final Drawable icon = direct_retweet.getIcon().mutate();
			direct_retweet.setVisible(seprate_retweet_action && (!status.is_protected || isMyRetweet(status)));
			if (isMyRetweet(status)) {
				icon.setColorFilter(activated_color, PorterDuff.Mode.MULTIPLY);
				direct_retweet.setTitle(R.string.cancel_retweet);
			} else {
				icon.clearColorFilter();
				direct_retweet.setTitle(R.string.retweet);
			}
		}
		mPopupMenu.setOnMenuItemClickListener(this);
		mPopupMenu.show();
	}

	abstract long[] getNewestStatusIds();

	abstract long[] getOldestStatusIds();

	void setListHeaderFooters(final ListView list) {

	}
}
