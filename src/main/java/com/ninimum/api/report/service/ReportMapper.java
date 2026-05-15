package com.ninimum.api.report.service;

import com.ninimum.api.dto.ReportDto;
import com.ninimum.api.param.AddReportParam;
import com.ninimum.api.param.ReportListParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReportMapper {
    int addReport(AddReportParam param) throws Exception;

    List<ReportDto> getReportList(ReportListParam param) throws Exception;
}