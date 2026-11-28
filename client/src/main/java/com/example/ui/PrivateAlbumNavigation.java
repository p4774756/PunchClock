package com.example.ui;

/** 私房相簿大圖檢視的索引邊界（單元測試與面板共用）。 */
public final class PrivateAlbumNavigation {

    private PrivateAlbumNavigation() {
    }

    public static int clampIndex(int index, int count) {
        if (count <= 0) {
            return -1;
        }
        if (index < 0) {
            return 0;
        }
        if (index >= count) {
            return count - 1;
        }
        return index;
    }

    public static int previousIndex(int index, int count) {
        if (count <= 0) {
            return -1;
        }
        if (index <= 0) {
            return 0;
        }
        return index - 1;
    }

    public static int nextIndex(int index, int count) {
        if (count <= 0) {
            return -1;
        }
        if (index >= count - 1) {
            return count - 1;
        }
        return index + 1;
    }
}