package com.ninimum.api.admin.management;

import com.ninimum.api.admin.management.service.AdminManagementMapper;
import com.ninimum.api.admin.management.service.impl.AdminManagementService;
import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.file.service.impl.FileService;
import com.ninimum.api.warehouse.WarehouseAppService;
import com.ninimum.api.warehouse.WarehouseService;
import com.ninimum.api.deliveryapp.service.ReturnPhotoService;
import com.ninimum.api.deliveryapp.service.PartialReturnService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryValidationTest {
    private CamelCaseMap category(long id, Long parent) {
        var row = new CamelCaseMap(); row.put("id", id); row.put("parent_id", parent); return row;
    }
    private AdminManagementService service(AdminManagementMapper mapper) {
        return new AdminManagementService(mapper, mock(FileService.class), mock(WarehouseAppService.class),
                mock(WarehouseService.class), mock(ReturnPhotoService.class), mock(PartialReturnService.class));
    }
    @Test void rejectsParentingAnAncestorUnderItsDescendant() {
        var mapper = mock(AdminManagementMapper.class);
        when(mapper.lockCategories()).thenReturn(List.of(category(1,null),category(2,1L),category(3,2L)));
        assertThrows(IllegalArgumentException.class, () -> service(mapper).updateCategory(1,Map.of("parent_id",3L)));
        verify(mapper,never()).updateCategory(anyMap());
    }
    @Test void trimsNamesAndPreservesExplicitParentRemoval() {
        var mapper = mock(AdminManagementMapper.class);
        when(mapper.lockCategories()).thenReturn(List.of(category(2,1L)));
        var request = new HashMap<String,Object>(); request.put("name","  Milk  "); request.put("parent_id",null);
        service(mapper).updateCategory(2,request);
        verify(mapper).updateCategory(argThat(data -> "Milk".equals(data.get("name")) &&
                data.containsKey("parent_id") && data.get("parent_id")==null));
        assertEquals("  Milk  ",request.get("name"));
    }
    @Test void rejectsBlankNamesAndMissingParents() {
        var mapper = mock(AdminManagementMapper.class);
        when(mapper.lockCategories()).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service(mapper).createCategory(Map.of("name","   ")));
        assertThrows(IllegalArgumentException.class, () -> service(mapper).createCategory(Map.of("name","Milk","parent_id",99)));
        verify(mapper,never()).insertCategory(anyMap());
    }
}
