package com.tileman.multiplayer.shared;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread safe HashMap of Sets.
 */
public class ConcurrentSetMap<K,V> {

    private ConcurrentHashMap<K, Set<V>> internalMap = new ConcurrentHashMap<>();

    public boolean add(K key, V val) {
        if (!internalMap.containsKey(key)) {
            Set<V> set = ConcurrentHashMap.newKeySet();
            internalMap.put(key, set);
        }
        return internalMap.get(key).add(val);
    }

    public void addAll(K key, Collection<V> val) {
        if (!internalMap.containsKey(key)) {
            Set<V> set = ConcurrentHashMap.newKeySet();
            internalMap.put(key, set);
        }
        internalMap.get(key).addAll(val);
    }

    public void addAll(K key, V[] val) {
        addAll(key, Arrays.asList(val));
    }

    public boolean remove(K key, V val) {
        if (!internalMap.containsKey(key)) {
            return false;
        }
        return internalMap.get(key).remove(val);
    }

    public Set<V> remove(K key) {
        return internalMap.remove(key);
    }

    public boolean containsKey(K key) {
        return internalMap.containsKey(key);
    }

    public ConcurrentHashMap.KeySetView<K, Set<V>> keySet() {
        return internalMap.keySet();
    }

    public Set<V> get(K key) {
        if (internalMap.containsKey(key)) {
            return internalMap.get(key);
        }
        return new HashSet<V>();
    }
}
