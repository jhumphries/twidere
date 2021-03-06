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

package org.mariotaku.twidere.model;

import static org.mariotaku.twidere.util.Utils.parseString;

import java.io.Serializable;

import twitter4j.User;
import twitter4j.UserList;
import android.os.Parcel;
import android.os.Parcelable;

public class ParcelableUserList implements Parcelable, Serializable, Comparable<ParcelableUserList> {

	private static final long serialVersionUID = 5896769285301886501L;

	public static final Parcelable.Creator<ParcelableUserList> CREATOR = new Parcelable.Creator<ParcelableUserList>() {
		@Override
		public ParcelableUserList createFromParcel(final Parcel in) {
			return new ParcelableUserList(in);
		}

		@Override
		public ParcelableUserList[] newArray(final int size) {
			return new ParcelableUserList[size];
		}
	};

	public final int list_id;

	public final long account_id, user_id, position;

	public final boolean is_public, is_following;

	public final String description, name, user_screen_name, user_name, user_profile_image_url_string;

	public ParcelableUserList(final Parcel in) {
		position = in.readLong();
		account_id = in.readLong();
		list_id = in.readInt();
		is_public = in.readInt() == 1;
		is_following = in.readInt() == 1;
		name = in.readString();
		description = in.readString();
		user_id = in.readLong();
		user_name = in.readString();
		user_screen_name = in.readString();
		user_profile_image_url_string = in.readString();
	}

	public ParcelableUserList(final UserList user, final long account_id) {
		this(user, account_id, 0);
	}

	public ParcelableUserList(final UserList list, final long account_id, final long position) {
		final User user = list.getUser();
		this.position = position;
		this.account_id = account_id;
		list_id = list.getId();
		is_public = list.isPublic();
		is_following = list.isFollowing();
		name = list.getName();
		description = list.getDescription();
		user_id = user.getId();
		user_name = user.getName();
		user_screen_name = user.getScreenName();
		user_profile_image_url_string = parseString(user.getProfileImageUrlHttps());
	}

	@Override
	public int compareTo(final ParcelableUserList another) {
		if (another == null) return 0;
		final long diff = position - another.position;
		if (diff > Integer.MAX_VALUE) return Integer.MAX_VALUE;
		if (diff < Integer.MIN_VALUE) return Integer.MIN_VALUE;
		return (int) diff;
	}

	@Override
	public int describeContents() {
		return hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (!(obj instanceof ParcelableUserList)) return false;
		final ParcelableUserList other = (ParcelableUserList) obj;
		if (account_id != other.account_id) return false;
		if (list_id != other.list_id) return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (account_id ^ account_id >>> 32);
		result = prime * result + list_id;
		return result;
	}

	@Override
	public String toString() {
		return "ParcelableUserList{list_id=" + list_id + ", account_id=" + account_id + ", user_id=" + user_id
				+ ", position=" + position + ", is_public=" + is_public + ", is_following=" + is_following
				+ ", description=" + description + ", name=" + name + ", user_screen_name=" + user_screen_name
				+ ", user_name=" + user_name + ", user_profile_image_url_string=" + user_profile_image_url_string + "}";
	}

	@Override
	public void writeToParcel(final Parcel out, final int flags) {
		out.writeLong(position);
		out.writeLong(account_id);
		out.writeInt(list_id);
		out.writeInt(is_public ? 1 : 0);
		out.writeInt(is_following ? 1 : 0);
		out.writeString(name);
		out.writeString(description);
		out.writeLong(user_id);
		out.writeString(user_name);
		out.writeString(user_screen_name);
		out.writeString(user_profile_image_url_string);

	}

}
