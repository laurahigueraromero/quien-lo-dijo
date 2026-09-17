package com.quienlodijo.backend.round;

import com.quienlodijo.backend.round.dto.AnswerRequest;
import com.quienlodijo.backend.round.dto.BetRequest;
import com.quienlodijo.backend.round.dto.WsErrorResponse;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * Acciones de cliente a servidor dentro de una ronda (spec.md US-4, plan.md §5, T017).
 * El {@link Principal} de la sesión STOMP lo asigna {@code StompAuthChannelInterceptor}
 * a partir del JWT del frame CONNECT; su nombre es el id de usuario.
 */
@Controller
public class RoundWebSocketController {

    private final GameEngineService gameEngineService;

    public RoundWebSocketController(GameEngineService gameEngineService) {
        this.gameEngineService = gameEngineService;
    }

    @MessageMapping("/rooms/{code}/answer")
    public void answer(@DestinationVariable String code, @Payload AnswerRequest request, Principal principal) {
        gameEngineService.submitAnswer(code, Long.valueOf(principal.getName()), request.text());
    }

    @MessageMapping("/rooms/{code}/bet")
    public void bet(@DestinationVariable String code, @Payload BetRequest request, Principal principal) {
        gameEngineService.submitBet(code, Long.valueOf(principal.getName()), request.candidateUserId(), request.amount());
    }

    /**
     * Sin este handler, una excepción en {@link #answer} solo queda registrada en el log del
     * servidor (comportamiento por defecto de Spring para @MessageMapping) y el cliente que
     * disparó la acción nunca se entera de que falló. Se envía solo a quien la causó, vía la
     * cola de usuario /user/queue/errors (verificado con un cliente STOMP real).
     */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public WsErrorResponse handleException(Exception ex) {
        return new WsErrorResponse(ex.getMessage() != null ? ex.getMessage() : "Error inesperado");
    }
}
