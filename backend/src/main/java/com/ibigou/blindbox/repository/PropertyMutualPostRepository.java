package com.ibigou.blindbox.repository;

import com.ibigou.blindbox.entity.PropertyMutualPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyMutualPostRepository extends JpaRepository<PropertyMutualPost, Long> {

    List<PropertyMutualPost> findByCompanyIdOrderByCreateTimeDesc(Long companyId);

    List<PropertyMutualPost> findByCompanyIdAndStatusOrderByCreateTimeDesc(Long companyId, Integer status);

    List<PropertyMutualPost> findByCompanyIdAndPostTypeAndStatusOrderByCreateTimeDesc(Long companyId, Integer postType, Integer status);

    List<PropertyMutualPost> findByOwnerIdOrderByCreateTimeDesc(Long ownerId);

    long countByCompanyIdAndStatus(Long companyId, Integer status);
}
