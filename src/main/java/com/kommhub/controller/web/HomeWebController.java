package com.kommhub.controller.web;

import com.kommhub.config.SiteProperties;
import com.kommhub.model.db.User;
import com.kommhub.model.dto.summary.MainUserSummary;
import com.kommhub.security.SecurityUtil;
import com.kommhub.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
@RequiredArgsConstructor
public class HomeWebController {

    private final SiteProperties siteProperties;
    private final SecurityUtil securityUtil;
    private final UserService userService;

    @ModelAttribute("site")
    public SiteProperties site() {
        return siteProperties;
    }

    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated() {
        return securityUtil.getCurrentUser() != null;
    }

    @ModelAttribute("user")
    public MainUserSummary currentUser() {
        User user = securityUtil.getCurrentUser();
        return user != null ? userService.toDto(user) : null;
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
