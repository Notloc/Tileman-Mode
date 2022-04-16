package com.tileman.multiplayer.shared;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
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

    public boolean remove(K key, V val) {
        if (!internalMap.containsKey(key)) {
            return false;
        }
        return internalMap.get(key).remove(val);
    }

    public boolean containsKey(K key) {
        return internalMap.containsKey(key);
    }

    public Set<V> get(K key) {
        if (internalMap.containsKey(key)) {
            return internalMap.get(key);
        }
        return new HashSet<V>();
    }
}
