package com.ninimum.api.deliveryapp.service.impl;

import com.ninimum.api.deliveryapp.service.DeliveryAppMapper;
import com.ninimum.api.deliveryapp.service.IDeliveryAppService;
import com.ninimum.api.dto.*;
import com.ninimum.api.param.CreateDeliveryAppWorkerParam;
import com.ninimum.api.param.DeliveryAppStatusParam;
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
    @Transactional
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
        return mapper.getDashboard(worker.getId());
    }

    @Override
    public List<DeliveryAppJobDto> getAvailableJobs(String workerId) throws Exception {
        getWorker(workerId);
        mapper.syncPaidOrders();
        return mapper.getAvailableJobs();
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
        // to the correct step: ACCEPTED -> PREPARING, ON_THE_WAY -> ON_THE_WAY.
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
    @Transactional
    public int claimJob(String workerId, Long jobId) throws Exception {
        if (jobId == null) throw new Exception("jobId is required");
        DeliveryAppWorkerDto worker = getWorker(workerId);
        int updated = mapper.claimJob(jobId, worker.getId());
        if (updated == 0) throw new Exception("This delivery was already taken by another courier");

        // ACCEPTED maps to PREPARING in the customer order, which Ninimum
        // displays as the third step: "Yetkazishga tayyor".
        mapper.syncOrderStatusFromJob(jobId);

        mapper.addTracking(jobId, worker.getId(), "ACCEPTED", "Delivery accepted by courier");
        return updated;
    }

    @Override
    @Transactional
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
            // Keep the customer order alive for admin review/reassignment.
        } else {
            throw new Exception("Invalid delivery status transition: " + current + " -> " + next);
        }

        int updated = mapper.updateJobStatus(param.getJobId(), worker.getId(), next, param.getNote());
        if (updated == 0) throw new Exception("Could not update delivery status");

        if (orderStatus != null) {
            mapper.updateOrderStatusForJob(param.getJobId(), orderStatus);
        }

        mapper.addTracking(param.getJobId(), worker.getId(), next,
                param.getNote() == null || param.getNote().isBlank() ? next : param.getNote());
        return updated;
    }
}
