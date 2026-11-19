package com.fundmessenger.drive.repository;

import com.fundmessenger.drive.entity.DriveFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriveFolderRepository extends JpaRepository<DriveFolder, Long> {

    List<DriveFolder> findByClubIdOrderByDisplayOrderAsc(Long clubId);

    Optional<DriveFolder> findByClubIdAndName(Long clubId, String name);
}
