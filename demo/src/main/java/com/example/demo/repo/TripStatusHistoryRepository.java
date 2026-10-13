package com.example.demo.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.domain.TripStatusHistory;

public interface TripStatusHistoryRepository extends JpaRepository<TripStatusHistory, Long> {
    List<TripStatusHistory> findByTrip_IdOrderByChangedAtAsc(Long tripId);
}