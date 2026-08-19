package com.ninimum.api.dto.payme;

import com.ninimum.api.param.PageSizeParam;
import lombok.Data;

@Data
public class FiscalMxikPackageListParam extends PageSizeParam {

    private String keyword;
}
