package com.github.sbadolankaur.rpg.content.web;

import com.github.sbadolankaur.rpg.content.ContentPacks;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class AttributionController {
  private final ContentPacks packs;

  AttributionController(ContentPacks packs) {
    this.packs = packs;
  }

  @GetMapping("/help")
  String help(Model model) {
    model.addAttribute("attribution", packs.notice());
    return "demo-attribution";
  }
}
