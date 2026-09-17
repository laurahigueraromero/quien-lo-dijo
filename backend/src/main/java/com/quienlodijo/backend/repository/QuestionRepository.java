package com.quienlodijo.backend.repository;

import com.quienlodijo.backend.domain.Question;
import com.quienlodijo.backend.domain.Room;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByRoom(Room room);

    List<Question> findByRoomAndDiscardedFalseOrderByPlayOrderAsc(Room room);
}
