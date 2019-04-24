package com.dataiku.dss.model.dss;

import com.google.common.base.Preconditions;

public class VersionTag {
    public long versionNumber;
    public VersionTagUser lastModifiedBy;
    public long lastModifiedOn;

    public static class VersionTagUser {
        public String login;
    }

    public VersionTag() {
    }

    public VersionTag(String lastModifiedBy) {
        this(lastModifiedBy, System.currentTimeMillis(), 0);
    }

    public VersionTag(String lastModifiedBy, long modifiedOn, long versionNumber) {
        VersionTagUser vtu = new VersionTagUser();
        vtu.login = Preconditions.checkNotNull(lastModifiedBy);
        this.lastModifiedBy = vtu;
        this.versionNumber = versionNumber;
        this.lastModifiedOn = modifiedOn;
    }

    @Override
    public String toString() {
        return "#" + versionNumber + "[" + lastModifiedBy.login + "]";
    }
}