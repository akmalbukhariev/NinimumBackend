package com.ninimum.api.report.service;

import com.ninimum.api.dto.ReportDto;
import com.ninimum.api.param.AddReportParam;
import com.ninimum.api.param.ReportListParam;

import java.util.List;

public interface IReportService {
    int addReport(AddReportParam param) throws Exception;

    List<ReportDto> getReportList(ReportListParam param) throws Exception;
}