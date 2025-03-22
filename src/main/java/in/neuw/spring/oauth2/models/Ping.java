package in.neuw.spring.oauth2.models;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Getter
@Setter
@Accessors(chain = true)
public class Ping {

    private String message;
    private LocalDateTime time;

}
