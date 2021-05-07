package com.myorg.myservice.utility;

import java.util.HashMap;
import java.util.Map;

public class MyOrgUtility {

	public static Map<String, String> singletonMap(String key, String value){
		HashMap<String, String> hashMap = new HashMap<String, String>();
		hashMap.put(key, value);
		return hashMap;
	}
	
	public static Map<String, String> singletonMap(String[] keys, String[] values){
		HashMap<String, String> hashMap = new HashMap<String, String>();
		if(keys == null || keys.length == 0 || values == null || values.length == 0 || keys.length != values.length) {
			throw new IllegalArgumentException("Must have two equal length arrays to create a map");
		}
		
		int index = 0;
		for(String key: keys) {
			hashMap.put(key, values[index]);
			index++;
		}
		return hashMap;
	}
}
