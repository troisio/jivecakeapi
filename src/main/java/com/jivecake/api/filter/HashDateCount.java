package com.jivecake.api.filter;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class HashDateCount {
    private final Map<Object, LinkedList<Date>> data = new HashMap<>();

    public List<Date> get(Object key) {
        return this.data.get(key);
    }

    public boolean containsKey(Object key) {
        return this.data.containsKey(key);
    }

    public void limitToLast(Object key, int length) {
        if (this.data.containsKey(key)) {
            LinkedList<Date> dates = this.data.get(key);

            if (dates.size() > length) {
                this.data.put(key, this.last(key, length));
            }
        }
    }

    public LinkedList<Date> last(Object key, int length) {
        LinkedList<Date> result;

        if (this.data.containsKey(key)) {
            LinkedList<Date> list = this.data.get(key);
            int startIndex = list.size() - length > 0 ? list.size() - length : 0;

            result = new LinkedList<>(list.subList(startIndex, list.size()));
        } else {
            result = null;
        }

        return result;
    }

    public void add(Object key, Date date) {
        if (this.data.containsKey(key)) {
            this.data.get(key).add(date);
        } else {
            this.data.put(key, new LinkedList<>(Arrays.asList(new Date())));
        }
    }

    @Override
    public String toString() {
        return this.data.toString();
    }
}