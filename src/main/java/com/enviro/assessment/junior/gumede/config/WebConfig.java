package com.enviro.assessment.junior.gumede.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// One CORS policy for the whole API, not @CrossOrigin scattered across each controller. Three reasons that
// matters in practice, not just style:
// 1. Every controller automatically gets the same policy - a new controller added later can't forget it or
//    diverge from it, because there's nothing per-controller to remember to add.
// 2. There's exactly one place to change allowed origins for a different environment (a deployed frontend
//    URL instead of localhost), instead of hunting through every @RestController for an annotation to edit.
// 3. It only opens up /api/**, not the H2 console or anything else Spring Boot exposes - @CrossOrigin on each
//    controller would achieve the same restriction, but only by accident of which classes happen to have it.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // allowedOriginPatterns("http://localhost:*") rather than a single hardcoded port: the frontend for
        // this project (Angular, React, or plain HTML/JS per the brief) isn't built yet, and each of those
        // typically dev-serves on a different port (4200, 3000, 5500, ...). This allows any local dev server
        // to call the API without guessing the port up front, while still refusing arbitrary internet origins
        // since the pattern is scoped to localhost. Tighten this to the actual deployed frontend origin(s)
        // before this goes anywhere near production.
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
