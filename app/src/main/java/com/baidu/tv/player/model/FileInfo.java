package com.baidu.tv.player.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

/**
 * 文件信息模型
 */
public class FileInfo implements Parcelable {
    @SerializedName("fs_id")
    private long fsId;
    
    private String path;
    
    @SerializedName("server_filename")
    private String serverFilename;
    
    private long size;
    
    @SerializedName("server_mtime")
    private long serverMtime;
    
    @SerializedName("server_ctime")
    private long serverCtime;
    
    @SerializedName("local_mtime")
    private long localMtime;
    
    @SerializedName("local_ctime")
    private long localCtime;
    
    private int isdir;
    
    private int category;
    
    private String md5;
    
    @SerializedName("dir_empty")
    private int dirEmpty;
    
    private Thumbs thumbs;
    
    private String dlink;

    // Parcelable构造函数
    protected FileInfo(Parcel in) {
        fsId = in.readLong();
        path = in.readString();
        serverFilename = in.readString();
        size = in.readLong();
        serverMtime = in.readLong();
        serverCtime = in.readLong();
        localMtime = in.readLong();
        localCtime = in.readLong();
        isdir = in.readInt();
        category = in.readInt();
        md5 = in.readString();
        dirEmpty = in.readInt();
        thumbs = in.readParcelable(Thumbs.class.getClassLoader());
        dlink = in.readString();
    }

    public static final Creator<FileInfo> CREATOR = new Creator<FileInfo>() {
        @Override
        public FileInfo createFromParcel(Parcel in) {
            return new FileInfo(in);
        }

        @Override
        public FileInfo[] newArray(int size) {
            return new FileInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(fsId);
        dest.writeString(path);
        dest.writeString(serverFilename);
        dest.writeLong(size);
        dest.writeLong(serverMtime);
        dest.writeLong(serverCtime);
        dest.writeLong(localMtime);
        dest.writeLong(localCtime);
        dest.writeInt(isdir);
        dest.writeInt(category);
        dest.writeString(md5);
        dest.writeInt(dirEmpty);
        dest.writeParcelable(thumbs, flags);
        dest.writeString(dlink);
    }

    // Getters and Setters
    public long getFsId() {
        return fsId;
    }

    public void setFsId(long fsId) {
        this.fsId = fsId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getServerFilename() {
        return serverFilename;
    }

    public void setServerFilename(String serverFilename) {
        this.serverFilename = serverFilename;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getServerMtime() {
        return serverMtime;
    }

    public void setServerMtime(long serverMtime) {
        this.serverMtime = serverMtime;
    }

    public long getServerCtime() {
        return serverCtime;
    }

    public void setServerCtime(long serverCtime) {
        this.serverCtime = serverCtime;
    }

    public long getLocalMtime() {
        return localMtime;
    }

    public void setLocalMtime(long localMtime) {
        this.localMtime = localMtime;
    }

    public long getLocalCtime() {
        return localCtime;
    }

    public void setLocalCtime(long localCtime) {
        this.localCtime = localCtime;
    }

    public int getIsdir() {
        return isdir;
    }

    public void setIsdir(int isdir) {
        this.isdir = isdir;
    }

    public int getCategory() {
        return category;
    }

    public void setCategory(int category) {
        this.category = category;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public int getDirEmpty() {
        return dirEmpty;
    }

    public void setDirEmpty(int dirEmpty) {
        this.dirEmpty = dirEmpty;
    }

    public Thumbs getThumbs() {
        return thumbs;
    }

    public void setThumbs(Thumbs thumbs) {
        this.thumbs = thumbs;
    }

    public String getDlink() {
        return dlink;
    }

    public void setDlink(String dlink) {
        this.dlink = dlink;
    }

    /**
     * 是否是目录
     */
    public boolean isDirectory() {
        return isdir == 1;
    }

    /**
     * 是否是图片
     */
    public boolean isImage() {
        if (serverFilename == null) return false;
        String ext = getExtension().toLowerCase();
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") ||
               ext.equals("avif") || ext.equals("webp") || ext.equals("heic") ||
               ext.equals("heif") || ext.equals("bmp") || ext.equals("gif") ||
               ext.equals("tiff") || ext.equals("tif");
    }

    /**
     * 是否是视频
     */
    public boolean isVideo() {
        if (serverFilename == null) return false;
        String ext = getExtension().toLowerCase();
        return ext.equals("mp4") || ext.equals("mov") || ext.equals("3gp") || 
               ext.equals("mkv") || ext.equals("avi");
    }

    /**
     * 获取文件扩展名
     */
    public String getExtension() {
        if (serverFilename == null || !serverFilename.contains(".")) {
            return "";
        }
        return serverFilename.substring(serverFilename.lastIndexOf(".") + 1);
    }

    /**
     * 缩略图信息
     */
    public static class Thumbs implements Parcelable {
        private String icon;
        private String url1;
        private String url2;
        private String url3;

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public String getUrl1() {
            return url1;
        }

        public void setUrl1(String url1) {
            this.url1 = url1;
        }

        public String getUrl2() {
            return url2;
        }

        public void setUrl2(String url2) {
            this.url2 = url2;
        }

        public String getUrl3() {
            return url3;
        }

        public void setUrl3(String url3) {
            this.url3 = url3;
        }

        // Parcelable构造函数
        protected Thumbs(Parcel in) {
            icon = in.readString();
            url1 = in.readString();
            url2 = in.readString();
            url3 = in.readString();
        }

        public static final Creator<Thumbs> CREATOR = new Creator<Thumbs>() {
            @Override
            public Thumbs createFromParcel(Parcel in) {
                return new Thumbs(in);
            }

            @Override
            public Thumbs[] newArray(int size) {
                return new Thumbs[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(icon);
            dest.writeString(url1);
            dest.writeString(url2);
            dest.writeString(url3);
        }
    }
}