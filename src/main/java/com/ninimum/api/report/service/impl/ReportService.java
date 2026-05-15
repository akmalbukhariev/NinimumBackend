package com.ninimum.api.report.service.impl;

import com.ninimum.api.dto.ReportDto;
import com.ninimum.api.param.AddReportParam;
import com.ninimum.api.param.ReportListParam;
import com.ninimum.api.report.service.IReportService;
import com.ninimum.api.report.service.ReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService implements IReportService {

    private final ReportMapper reportMapper;

    @Override
    public int addReport(AddReportParam param) throws Exception {
        return this.reportMapper.addReport(param);
    }

    @Override
    public List<ReportDto> getReportList(ReportListParam param) throws Exception {
        return this.reportMapper.getReportList(param);
    }
}