package com.ninimum.api.deliveryapp.service;

import com.ninimum.api.dto.*;
import com.ninimum.api.param.CreateDeliveryAppWorkerParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeliveryAppMapper {
    DeliveryAppWorkerAuthDto getWorkerAuthByWorkerId(@Param("workerId") String workerId);
    DeliveryAppWorkerDto getWorkerByWorkerId(@Param("workerId") String workerId);
    int createWorker(CreateDeliveryAppWorkerParam param);
    List<DeliveryAppWorkerDto> getWorkers();
    int setWorkerOnline(@Param("workerId") Long workerId, @Param("online") boolean online);
    int syncPaidOrders();

    DeliveryAppDashboardDto getDashboard(@Param("workerId") Long workerId);
    List<DeliveryAppJobDto> getAvailableJobs();
    List<DeliveryAppJobDto> getActiveJobs(@Param("workerId") Long workerId);
    List<DeliveryAppJobDto> getHistoryJobs(@Param("workerId") Long workerId);
    DeliveryAppJobDto getJobDetail(@Param("jobId") Long jobId, @Param("workerId") Long workerId);
    List<DeliveryAppJobProductDto> getJobProducts(@Param("jobId") Long jobId);

    int claimJob(@Param("jobId") Long jobId, @Param("workerId") Long workerId);
    String getOwnedJobStatus(@Param("jobId") Long jobId, @Param("workerId") Long workerId);
    int updateJobStatus(@Param("jobId") Long jobId, @Param("workerId") Long workerId,
                        @Param("status") String status, @Param("note") String note);
    int updateOrderStatusForJob(@Param("jobId") Long jobId, @Param("orderStatus") String orderStatus);
    int addTracking(@Param("jobId") Long jobId, @Param("workerId") Long workerId,
                    @Param("status") String status, @Param("message") String message);
}
