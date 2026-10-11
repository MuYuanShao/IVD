package com.zlth.ivd.laboratory.storage;
/** Only encrypted bytes cross this boundary. Objects are never served by a public URL. */
public interface ReportObjectStore {
    void put(String key,byte[] encrypted);
    byte[] get(String key);
    void remove(String key);
}
