package model;

import com.avaje.ebean.Model;
import com.avaje.ebean.Expr;
import com.google.common.base.MoreObjects;
import play.Logger;

import javax.annotation.Nullable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.List;

@Entity
public class User extends Model {

    private static final String userIdColumnName = "user_id";
    private static final String displayNameColumnName = "display_name";
    private static final String onlineColumnName = "online";

    public static class Builder{
        private String id;
        private String userId;
        private String displayName;
        private boolean online;

        public Builder(final String id,
                       final String userId,
                       final String displayName) {
            this.id = id;
            this.userId = userId;
            this.displayName = displayName;
        }

        public Builder online(final boolean isOnline) {
            this.online = isOnline;
            return this;
        }

        public User build() {
            final User user = new User(userId, displayName, online);
            user.id = id;
            return user;
        }
    }

    @Id
    private String id;
    @Column(unique=true, name=userIdColumnName)
    private final String userId;
    private final String displayName;
    private final boolean online;

    public User(final String userId,
                final String displayName,
                final boolean isOnline) {
        this.userId = userId;
        this.displayName = displayName;
        this.online = isOnline;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isOnline() {
        return online;
    }

    public Builder copy() {
        return new Builder(id, userId, displayName).online(online);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(id)
                .addValue(userId)
                .addValue(displayName)
                .addValue(online)
                .omitNullValues()
                .toString();
    }

    public static Finder<String, User> find = new Finder<>(User.class);

    @Nullable
    public static User findByUserId(final String username) {
        try {
            List<User> users = find.where().eq(userIdColumnName, username).findList();

            final User result;
            if (users.isEmpty()) {
                result = null;
            } else {
                result = users.get(0);
            }

            return result;
        } catch (final Exception ex) {
            Logger.error("Finding user by user id failed!", ex);
        }

        return null;
    }

    public static List<User> findOnlineUsersByDisplayName(final String searchPhrase) {
        try {
            return find
                    .where()
                    .and(Expr.like(displayNameColumnName, "%" + searchPhrase + "%"),
                            Expr.eq(onlineColumnName, true)).findList();
        } catch (final Exception ex) {
            Logger.error("Finding user by user name failed!", ex);
        }
        return null;
    }


}
