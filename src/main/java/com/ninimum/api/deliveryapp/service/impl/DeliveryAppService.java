package com.ninimum.api.deliveryapp.service.impl;

import com.ninimum.api.deliveryapp.service.DeliveryAppMapper;
import com.ninimum.api.deliveryapp.service.IDeliveryAppService;
import com.ninimum.api.dto.*;
import com.ninimum.api.param.CreateDeliveryAppWorkerParam;
import com.ninimum.api.param.DeliveryAppStatusParam;
import com.ninimum.api.param.DeliveryAppBatchJobParam;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryAppService implements IDeliveryAppService {

    private final DeliveryAppMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final com.ninimum.api.warehouse.WarehouseAppService warehouseApp;

    @Value("${file.access.url}")
    private String fileAccessUrl;

    @Override
    public DeliveryAppWorkerDto authenticate(String workerId, String password) throws Exception {
        if (workerId == null || workerId.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new Exception("Courier ID and password are required");
        }

        DeliveryAppWorkerAuthDto auth = mapper.getWorkerAuthByWorkerId(workerId.trim());
        if (auth == null || !"ACTIVE".equalsIgnoreCase(auth.getStatus())) {
            throw new Exception("Delivery worker not found or inactive");
        }

        if (!passwordEncoder.matches(password, auth.getPasswordHash())) {
            throw new Exception("Password is incorrect");
        }

        return mapper.getWorkerByWorkerId(workerId.trim());
    }

    @Override
    public DeliveryAppWorkerDto getWorker(String workerId) throws Exception {
        DeliveryAppWorkerDto worker = mapper.getWorkerByWorkerId(workerId);
        if (worker == null || !"ACTIVE".equalsIgnoreCase(worker.getStatus())) {
            throw new Exception("Delivery worker not found or inactive");
        }
        return worker;
    }

    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public DeliveryAppWorkerDto createWorker(CreateDeliveryAppWorkerParam param) throws Exception {
        if (param == null || param.getWorkerId() == null || param.getWorkerId().trim().isEmpty()
                || param.getFullName() == null || param.getFullName().trim().isEmpty()
                || param.getPassword() == null || param.getPassword().length() < 6) {
            throw new Exception("Courier ID, full name and password (minimum 6 characters) are required");
        }

        String workerId = param.getWorkerId().trim().toUpperCase();
        if (!workerId.matches("[A-Z0-9._-]{3,50}")) {
            throw new Exception("Courier ID must be 3-50 characters and contain only letters, numbers, dot, dash or underscore");
        }
        if (mapper.getWorkerAuthByWorkerId(workerId) != null) {
            throw new Exception("Delivery worker with this ID already exists");
        }

        param.setWorkerId(workerId);
        param.setFullName(param.getFullName().trim());
        if (param.getPhoneNumber() != null) {
            String phone = param.getPhoneNumber().trim();
            param.setPhoneNumber(phone.isEmpty() ? null : phone);
        }
        param.setPassword(passwordEncoder.encode(param.getPassword()));

        if (mapper.createWorker(param) != 1) {
            throw new Exception("Could not create delivery worker");
        }

        return mapper.getWorkerByWorkerId(param.getWorkerId());
    }

    @Override
    public List<DeliveryAppWorkerDto> getWorkers() {
        return mapper.getWorkers();
    }

    @Override
    public int setOnline(String workerId, boolean online) throws Exception {
        DeliveryAppWorkerDto worker = getWorker(workerId);
        return mapper.setWorkerOnline(worker.getId(), online);
    }

    @Override
    public DeliveryAppDashboardDto getDashboard(String workerId) throws Exception {
        DeliveryAppWorkerDto worker = getWorker(workerId);
        mapper.syncPaidOrders();
        var dashboard=mapper.getDashboard(worker.getId());
        if(warehouseApp.isEnabled()) dashboard.setAvailableCount((int)mapper.getAvailableJobs().stream().filter(j -> warehouseApp.canDeliver(j.getOrderId())).count());
        return dashboard;
    }

    @Override
    public List<DeliveryAppJobDto> getAvailableJobs(String workerId) throws Exception {
        getWorker(workerId);
        mapper.syncPaidOrders();
        return mapper.getAvailableJobs().stream().filter(j -> warehouseApp.canDeliver(j.getOrderId())).toList();
    }

    @Override
    public List<DeliveryAppJobDto> getActiveJobs(String workerId) throws Exception {
        DeliveryAppWorkerDto worker = getWorker(workerId);
        return mapper.getActiveJobs(worker.getId());
    }

    @Override
    public List<DeliveryAppJobDto> getHistoryJobs(String workerId) throws Exception {
        DeliveryAppWorkerDto worker = getWorker(workerId);
        return mapper.getHistoryJobs(worker.getId());
    }

    @Override
    public DeliveryAppJobDto getJobDetail(String workerId, Long jobId) throws Exception {
        if (jobId == null) throw new Exception("jobId is required");
        DeliveryAppWorkerDto worker = getWorker(workerId);

        // Self-heal customer order status from the courier job. This is important
        // for jobs that were already ACCEPTED before the synchronization change
        // was deployed. Opening the delivery detail immediately brings Ninimum
        // to the correct step: ACCEPTED -> READY, ON_THE_WAY -> ON_THE_WAY.
        mapper.syncOrderStatusFromJob(jobId);

        DeliveryAppJobDto job = mapper.getJobDetail(jobId, worker.getId());
        if (job == null) throw new Exception("Delivery job not found");

        List<DeliveryAppJobProductDto> products = mapper.getJobProducts(jobId);
        if (products != null) {
            for (DeliveryAppJobProductDto product : products) {
                String image = product.getProductImageUrl();
                if (image != null && !image.isBlank() && !image.startsWith("http://") && !image.startsWith("https://")) {
                    product.setProductImageUrl(fileAccessUrl + "/" + image);
                }
            }
            job.setProducts(products);
        }
        return job;
    }

    @Override
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public int claimJob(String workerId, Long jobId) throws Exception {
        if (jobId == null) throw new Exception("jobId is required");
        DeliveryAppWorkerDto worker = getWorker(workerId);
        warehouseApp.requireReadyJob(jobId);
        int updated = mapper.claimJob(jobId, worker.getId());
        if (updated == 0) throw new Exception("This delivery was already taken by another courier");

        // ACCEPTED maps to READY in the customer order, which Ninimum
        // displays as the third step: "Yetkazishga tayyor".
        mapper.syncOrderStatusFromJob(jobId);

        mapper.addTracking(jobId, worker.getId(), "ACCEPTED", "Delivery accepted by courier");
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public int claimJobs(String workerId, DeliveryAppBatchJobParam param) throws Exception {
        if (param == null || param.getJobIds() == null || param.getJobIds().isEmpty()) {
            throw new Exception("At least one delivery must be selected");
        }

        DeliveryAppWorkerDto worker = getWorker(workerId);
        int claimed = 0;
        for (Long jobId : param.getJobIds().stream().filter(java.util.Objects::nonNull).distinct().sorted().toList()) {
            if (jobId == null) continue;
            warehouseApp.requireReadyJob(jobId);
        int updated = mapper.claimJob(jobId, worker.getId());
            if (updated == 0) {
                throw new Exception("One of the selected deliveries was already taken by another courier");
            }
            mapper.syncOrderStatusFromJob(jobId);
            mapper.addTracking(jobId, worker.getId(), "ACCEPTED", "Delivery accepted by courier as part of a delivery route");
            claimed += updated;
        }
        if (claimed == 0) throw new Exception("At least one delivery must be selected");
        return claimed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public int startJobs(String workerId, DeliveryAppBatchJobParam param) throws Exception {
        if (param == null || param.getJobIds() == null || param.getJobIds().isEmpty()) {
            throw new Exception("At least one delivery must be selected");
        }

        DeliveryAppWorkerDto worker = getWorker(workerId);
        int started = 0;
        for (Long jobId : param.getJobIds().stream().filter(java.util.Objects::nonNull).distinct().sorted().toList()) {
            if (jobId == null) continue;
            String current = mapper.getOwnedJobStatus(jobId, worker.getId());
            if (!"ACCEPTED".equals(current)) {
                throw new Exception("All selected deliveries must be accepted before starting the route");
            }
            int updated = mapper.updateJobStatus(jobId, worker.getId(), "ON_THE_WAY", null);
            if (updated == 0) throw new Exception("Could not start one of the selected deliveries");
            mapper.updateOrderStatusForJob(jobId, "ON_THE_WAY");
            mapper.addTracking(jobId, worker.getId(), "ON_THE_WAY", "Courier started a multi-order delivery route");
            started += updated;
        }
        if (started == 0) throw new Exception("At least one delivery must be selected");
        return started;
    }

    @Override
    @Transactional(rollbackFor = Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public int updateStatus(String workerId, DeliveryAppStatusParam param) throws Exception {
        if (param == null || param.getJobId() == null || param.getStatus() == null) {
            throw new Exception("jobId and status are required");
        }

        DeliveryAppWorkerDto worker = getWorker(workerId);
        String current = mapper.getOwnedJobStatus(param.getJobId(), worker.getId());
        if (current == null) throw new Exception("Delivery job does not belong to this courier");

        String next = param.getStatus().trim().toUpperCase();
        String orderStatus = null;
        if ("ACCEPTED".equals(current) && "ON_THE_WAY".equals(next)) {
            orderStatus = "ON_THE_WAY";
        } else if ("ON_THE_WAY".equals(current) && "DELIVERED".equals(next)) {
            orderStatus = "DELIVERED";
        } else if (("ACCEPTED".equals(current) || "ON_THE_WAY".equals(current)) && "FAILED".equals(next)) {
            String reason = param.getNote() == null ? "" : param.getNote().trim();
            if (reason.isEmpty() || reason.length() > 500) throw new Exception("A return reason of 1-500 characters is required");
            param.setNote(reason);
            orderStatus = "RETURNING";
        } else {
            throw new Exception("Invalid delivery status transition: " + current + " -> " + next);
        }

        int updated = mapper.updateJobStatus(param.getJobId(), worker.getId(), next, param.getNote());
        if (updated == 0) throw new Exception("Could not update delivery status");

        if (orderStatus != null) {
            if (mapper.updateOrderStatusForJob(param.getJobId(), orderStatus) != 1) throw new Exception("Order status changed; refresh the delivery");
        }

        mapper.addTracking(param.getJobId(), worker.getId(), next,
                param.getNote() == null || param.getNote().isBlank() ? next : param.getNote());
        return updated;
    }
}
