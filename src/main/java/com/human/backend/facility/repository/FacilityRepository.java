package com.human.backend.facility.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.backend.facility.entity.Facility;

public interface FacilityRepository extends JpaRepository<Facility, Long> {
}
