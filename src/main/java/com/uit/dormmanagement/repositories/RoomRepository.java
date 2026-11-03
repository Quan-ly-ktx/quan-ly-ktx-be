package com.uit.dormmanagement.repositories;

import com.uit.dormmanagement.entities.Room;
import com.uit.dormmanagement.enums.GenderPolicy;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {
  Optional<Room> findByRoomNumber(String roomNumber);

  boolean existsByRoomNumber(String roomNumber);

  List<Room> findByFloor(int floor);

  List<Room> findByGenderPolicy(GenderPolicy genderPolicy);

  List<Room> findByFloorAndGenderPolicy(int floor, GenderPolicy genderPolicy);

  default List<Room> findRoomsWithFilters(Integer floor, GenderPolicy genderPolicy, String search) {
    return findAll(
        createRoomFilterSpecification(floor, genderPolicy, search),
        Sort.by(Sort.Direction.ASC, "floor", "roomNumber"));
  }

  default Page<Room> findRoomsPaged(
      Integer floor, GenderPolicy genderPolicy, String search, Pageable pageable) {
    return findAll(createRoomFilterSpecification(floor, genderPolicy, search), pageable);
  }

  static Specification<Room> createRoomFilterSpecification(
      Integer floor, GenderPolicy genderPolicy, String search) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (floor != null) {
        predicates.add(cb.equal(root.get("floor"), floor));
      }
      if (genderPolicy != null) {
        predicates.add(cb.equal(root.get("genderPolicy"), genderPolicy));
      }
      if (search != null && !search.isBlank()) {
        String pattern = "%" + search.trim().toLowerCase() + "%";
        predicates.add(cb.like(cb.lower(root.get("roomNumber")), pattern));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
