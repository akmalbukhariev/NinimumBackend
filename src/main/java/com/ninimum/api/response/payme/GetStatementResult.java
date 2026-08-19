package com.ninimum.api.response.payme;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class GetStatementResult {
    private List<Map<String, Object>> transactions;
}