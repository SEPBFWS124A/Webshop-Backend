package de.fhdw.webshop.agb;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgbDataInitializer implements ApplicationRunner {

    private final AgbService agbService;

    @Override
    public void run(ApplicationArguments args) {
        agbService.seedInitialAgbIfMissing();
    }
}
