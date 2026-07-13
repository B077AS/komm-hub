package com.kommhub.controller.web;

import com.kommhub.config.SiteProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
@RequiredArgsConstructor
public class HomeWebController {

    private final SiteProperties siteProperties;

    @ModelAttribute("site")
    public SiteProperties site() {
        return siteProperties;
    }

    @GetMapping({"/", "/home"})
    public String homePage(Model model) {
        model.addAttribute("canonicalPath", "/");
        return "home";
    }

    @GetMapping("/download")
    public String downloadPage(Model model) {
        model.addAttribute("canonicalPath", "/download");
        return "download";
    }
}
