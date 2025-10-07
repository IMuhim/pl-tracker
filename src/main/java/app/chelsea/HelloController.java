package app.chelsea;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HelloController {
    @GetMapping("/")
    String ok() { return "Chelsea tracker is running"; }
}
