package model;

import com.avaje.ebean.Model;
import com.google.common.base.MoreObjects;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class User extends Model {

    public static class Builder{
        private String id;
        private String displayName;
        private boolean online;

        public Builder(final String id,
                       final String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder online(final boolean isOnline) {
            this.online = isOnline;
            return this;
        }

        public User build() {
            return new User(id, displayName, online);
        }
    }

    @Id
    private final String id;
    private final String displayName;
    private final boolean online;

    public User(final String id,
                final String displayName,
                final boolean isOnline) {
        this.id = id;
        this.displayName = displayName;
        this.online = isOnline;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isOnline() {
        return online;
    }

    public Builder copy() {
        return new Builder(id, displayName).online(online);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(id)
                .addValue(displayName)
                .addValue(online)
                .omitNullValues()
                .toString();
    }

    public static Finder<String, User> find = new Finder<>(User.class);
}
