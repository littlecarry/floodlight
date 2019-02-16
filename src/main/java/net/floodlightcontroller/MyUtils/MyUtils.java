package net.floodlightcontroller.MyUtils;

import java.util.List;
import java.util.Map;

public class MyUtils <T>{

    public <T> void mapCounter(Map<T, Integer> map, T key, Integer intial){
        if(map.containsKey(key)){
            int value = map.get(key);
            value++;
            map.put(key, value);
        }
        else
            map.put(key,intial);
    }


}
