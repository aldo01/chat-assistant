(function () {
  "use strict";

  var script = document.currentScript;
  if (!script || script.dataset.chatWidgetLoaded === "true") {
    return;
  }
  script.dataset.chatWidgetLoaded = "true";

  var apiUrl = script.dataset.api || new URL("/chat", window.location.origin).href;
  var brand = script.dataset.brand || "Chat Assistant";
  var greeting = script.dataset.greeting || "Hi! How can I help?";
  var accent = script.dataset.accent || "#2563eb";

  loadStyles(script.src);

  var widget = element("section", "caw-widget");
  widget.style.setProperty("--caw-accent", accent);
  widget.setAttribute("aria-label", brand + " chat");

  var launcher = element("button", "caw-launcher");
  launcher.type = "button";
  launcher.setAttribute("aria-label", "Open chat");
  launcher.setAttribute("aria-expanded", "false");
  launcher.innerHTML =
    '<svg viewBox="0 0 24 24" aria-hidden="true">' +
    '<path d="M4 4h16v12H7l-3 3V4zm3 5h10M7 12h7"></path></svg>';

  var panel = element("div", "caw-panel");
  panel.hidden = true;

  var header = element("header", "caw-header");
  var heading = element("div", "caw-heading");
  var title = element("strong", "caw-title", brand);
  var status = element("span", "caw-status", "Online");
  heading.append(title, status);

  var close = element("button", "caw-close", "\u00d7");
  close.type = "button";
  close.setAttribute("aria-label", "Close chat");
  header.append(heading, close);

  var messages = element("div", "caw-messages");
  messages.setAttribute("role", "log");
  messages.setAttribute("aria-live", "polite");
  messages.setAttribute("aria-relevant", "additions text");

  var form = element("form", "caw-form");
  var input = element("textarea", "caw-input");
  input.name = "question";
  input.placeholder = "Ask a question\u2026";
  input.rows = 1;
  input.maxLength = 2000;
  input.required = true;
  input.setAttribute("aria-label", "Message");

  var send = element("button", "caw-send", "Send");
  send.type = "submit";
  form.append(input, send);
  panel.append(header, messages, form);
  widget.append(panel, launcher);
  document.body.append(widget);

  var sessionId = createSessionId();
  var history = [];
  var activeController = null;

  addMessage("assistant", greeting);

  function setOpen(open) {
    panel.hidden = !open;
    launcher.setAttribute("aria-expanded", String(open));
    launcher.setAttribute("aria-label", open ? "Close chat" : "Open chat");
    if (open) {
      input.focus();
    }
  }

  launcher.addEventListener("click", function () {
    setOpen(panel.hidden);
  });
  close.addEventListener("click", function () {
    setOpen(false);
    launcher.focus();
  });

  input.addEventListener("keydown", function (event) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      form.requestSubmit();
    }
  });

  input.addEventListener("input", function () {
    input.style.height = "auto";
    input.style.height = Math.min(input.scrollHeight, 120) + "px";
  });

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    var question = input.value.trim();
    if (!question || activeController) {
      return;
    }

    var requestHistory = history.slice(-12);
    addMessage("user", question);
    input.value = "";
    input.style.height = "auto";
    setBusy(true);

    var answer = addMessage("assistant", "", true);
    activeController = new AbortController();

    try {
      var finalResponse = await streamAnswer(
        {
          sessionId: sessionId,
          question: question,
          history: requestHistory
        },
        answer,
        activeController.signal
      );

      var finalText = finalResponse && finalResponse.answer
        ? finalResponse.answer
        : answer.text.textContent;
      answer.text.textContent = finalText;
      answer.message.classList.remove("caw-pending");
      renderCitations(answer.message, finalResponse && finalResponse.citations);
      history.push(
        { role: "user", content: question },
        { role: "assistant", content: finalText }
      );
    } catch (error) {
      answer.message.classList.remove("caw-pending");
      answer.message.classList.add("caw-error");
      answer.text.textContent =
        error.name === "AbortError"
          ? "The request was cancelled."
          : "Sorry, I couldn't get a response. Please try again.";
      console.error("Chat widget request failed:", error);
    } finally {
      activeController = null;
      setBusy(false);
      input.focus();
    }
  });

  async function streamAnswer(payload, answer, signal) {
    var response = await fetch(apiUrl, {
      method: "POST",
      headers: {
        Accept: "text/event-stream",
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload),
      signal: signal
    });

    if (!response.ok) {
      throw new Error("Chat API returned HTTP " + response.status);
    }
    if (!response.body) {
      throw new Error("Streaming responses are not supported by this browser");
    }

    var reader = response.body.getReader();
    var decoder = new TextDecoder();
    var buffer = "";
    var finalResponse = null;

    while (true) {
      var chunk = await reader.read();
      buffer += decoder.decode(chunk.value || new Uint8Array(), {
        stream: !chunk.done
      });
      var frames = buffer.split(/\r?\n\r?\n/);
      buffer = frames.pop() || "";

      frames.forEach(function (frame) {
        var event = parseEvent(frame);
        if (!event) {
          return;
        }
        if (event.name === "token") {
          answer.text.textContent += parseToken(event.data);
          answer.message.classList.remove("caw-pending");
          scrollMessages();
        } else if (event.name === "citations") {
          finalResponse = JSON.parse(event.data);
        }
      });

      if (chunk.done) {
        break;
      }
    }
    return finalResponse;
  }

  function parseEvent(frame) {
    var name = "message";
    var data = [];
    frame.split(/\r?\n/).forEach(function (line) {
      if (line.indexOf("event:") === 0) {
        name = line.slice(6).trim();
      } else if (line.indexOf("data:") === 0) {
        data.push(line.slice(5).replace(/^ /, ""));
      }
    });
    return data.length ? { name: name, data: data.join("\n") } : null;
  }

  function parseToken(data) {
    if (data.charAt(0) === '"') {
      try {
        return JSON.parse(data);
      } catch (_error) {
        return data;
      }
    }
    return data;
  }

  function addMessage(role, text, pending) {
    var message = element("div", "caw-message caw-" + role);
    if (pending) {
      message.classList.add("caw-pending");
    }
    var content = element("div", "caw-bubble");
    var body = element("span", "caw-text", text);
    content.append(body);
    message.append(content);
    messages.append(message);
    scrollMessages();
    return { message: message, text: body };
  }

  function renderCitations(message, citations) {
    if (!Array.isArray(citations) || citations.length === 0) {
      return;
    }
    var list = element("div", "caw-citations");
    var label = element("span", "caw-citations-label", "Sources");
    list.append(label);

    citations.forEach(function (citation, index) {
      var url = safeHttpUrl(citation.url);
      if (!url) {
        return;
      }
      var link = element(
        "a",
        "caw-citation",
        citation.title || new URL(url).hostname || "Source " + (index + 1)
      );
      link.href = url;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      list.append(link);
    });

    if (list.childElementCount > 1) {
      message.querySelector(".caw-bubble").append(list);
    }
  }

  function safeHttpUrl(value) {
    try {
      var url = new URL(value);
      return url.protocol === "http:" || url.protocol === "https:"
        ? url.href
        : null;
    } catch (_error) {
      return null;
    }
  }

  function setBusy(busy) {
    input.disabled = busy;
    send.disabled = busy;
    send.textContent = busy ? "\u2026" : "Send";
  }

  function scrollMessages() {
    messages.scrollTop = messages.scrollHeight;
  }

  function loadStyles(scriptUrl) {
    if (document.querySelector("link[data-chat-widget-style]")) {
      return;
    }
    var link = document.createElement("link");
    link.rel = "stylesheet";
    link.dataset.chatWidgetStyle = "true";
    link.href = new URL("chat-widget.css", scriptUrl).href;
    document.head.append(link);
  }

  function createSessionId() {
    if (window.crypto && typeof window.crypto.randomUUID === "function") {
      return window.crypto.randomUUID();
    }
    return "chat-" + Date.now() + "-" + Math.random().toString(16).slice(2);
  }

  function element(tag, className, text) {
    var node = document.createElement(tag);
    node.className = className;
    if (text !== undefined) {
      node.textContent = text;
    }
    return node;
  }
})();
