package com.fundmessenger.club.repository;

import com.fundmessenger.club.entity.AdminFolder;
import com.fundmessenger.club.entity.AdminFolderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminFolderRepository extends JpaRepository<AdminFolder, Long> {

    List<AdminFolder> findByClubIdAndFolderTypeOrderByDisplayOrderAsc(Long clubId, AdminFolderType folderType);

    List<AdminFolder> findByClubIdOrderByDisplayOrderAsc(Long clubId);
}
