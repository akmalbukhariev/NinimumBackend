package com.ninimum.api.deliveryapp.service;

import com.ninimum.api.dto.*;
import com.ninimum.api.param.CreateDeliveryAppWorkerParam;
import com.ninimum.api.param.DeliveryAppStatusParam;

import java.util.List;

public interface IDeliveryAppService {
    DeliveryAppWorkerDto authenticate(String workerId, String password) throws Exception;
    DeliveryAppWorkerDto getWorker(String workerId) throws Exception;
    DeliveryAppWorkerDto createWorker(CreateDeliveryAppWorkerParam param) throws Exception;
    List<DeliveryAppWorkerDto> getWorkers() throws Exception;
    int setOnline(String workerId, boolean online) throws Exception;
    DeliveryAppDashboardDto getDashboard(String workerId) throws Exception;
    List<DeliveryAppJobDto> getAvailableJobs(String workerId) throws Exception;
    List<DeliveryAppJobDto> getActiveJobs(String workerId) throws Exception;
    List<DeliveryAppJobDto> getHistoryJobs(String workerId) throws Exception;
    DeliveryAppJobDto getJobDetail(String workerId, Long jobId) throws Exception;
    int claimJob(String workerId, Long jobId) throws Exception;
    int updateStatus(String workerId, DeliveryAppStatusParam param) throws Exception;
}
