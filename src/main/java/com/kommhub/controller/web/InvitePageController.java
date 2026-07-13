package com.kommhub.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class InvitePageController {

    @GetMapping("/invite/{code}")
    public String invitePage(@PathVariable String code, Model model) {
        model.addAttribute("inviteCode", code);
        return "invite";
    }
}
