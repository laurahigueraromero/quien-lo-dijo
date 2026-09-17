package com.quienlodijo.backend.repository;

import com.quienlodijo.backend.domain.Answer;
import com.quienlodijo.backend.domain.Round;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findByRound(Round round);

    List<Answer> findByRoundAndTextIsNotNull(Round round);
}
