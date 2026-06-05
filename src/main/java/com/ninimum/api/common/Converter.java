package com.ninimum.api.common;


import com.ninimum.api.camelcase.CamelCaseMap;

import java.util.ArrayList;
import java.util.List;

public class Converter {

    /**
     * Utility method to map a list of {@code CamelCaseMap} objects to a list of DTOs of the specified type.
     *
     * @param <T> The type of the DTO class to map to.
     * @param dataMap A list of {@code CamelCaseMap} objects representing the raw data.
     * @param targetClass The class of the target DTO type.
     * @return A list of objects of the specified DTO type.
     * @throws RuntimeException if mapping fails for any {@code CamelCaseMap}.
     */
    public static <T> List<T> mapToDtoList(List<CamelCaseMap> dataMap, Class<T> targetClass) {
        List<T> resultData = new ArrayList<>();
        for (CamelCaseMap map : dataMap) {
            try {
                resultData.add(map.toObject(targetClass));
            } catch (Exception e) {
                throw new RuntimeException("Failed to map CamelCaseMap to " + targetClass.getSimpleName(), e);
            }
        }
        return resultData;
    }
}
