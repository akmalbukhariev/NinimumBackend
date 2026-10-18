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
    @Test void validatesImageContentAndReencodesBeforeStorage() throws Exception {
        var files=mock(FileService.class);
        var service=new AdminManagementService(mock(AdminManagementMapper.class),files,mock(WarehouseAppService.class),mock(WarehouseService.class),mock(ReturnPhotoService.class),mock(PartialReturnService.class));
        assertThrows(Exception.class,()->service.uploadCategoryImage(Map.of("base64",Base64.getEncoder().encodeToString("not an image".getBytes()))));
        assertThrows(IllegalArgumentException.class,()->service.uploadCategoryImage(Map.of("base64","A".repeat(2800001))));
        verifyNoInteractions(files);
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var png=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",png);
        when(files.saveProductImage(any(byte[].class),eq("category.png"))).thenReturn("products/test.png");
        assertEquals("products/test.png",service.uploadCategoryImage(Map.of("base64",Base64.getEncoder().encodeToString(png.toByteArray()))));
        verify(files).saveProductImage(argThat(bytes->bytes.length>0),eq("category.png"));
    }
    @Test void rejectsOversizeTranslationsAndUnsafeImageUrls() {
        var mapper=mock(AdminManagementMapper.class);
        for(String url:List.of("javascript:alert(1)","file:///etc/passwd","products/../../file","https://user:pass@example.com/image.png"))
            assertThrows(IllegalArgumentException.class,()->service(mapper).createCategory(Map.of("name","Milk","image_url",url)));
        assertThrows(IllegalArgumentException.class,()->service(mapper).createCategory(Map.of("name","Milk","name_ru","x".repeat(256))));
        verify(mapper,never()).insertCategory(anyMap());
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
