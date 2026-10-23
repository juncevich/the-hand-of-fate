<#--
  Every notification email of the app (MailTemplates): an invitation or a draw result, in the look
  of Keycloak's account emails (frontend/src/email/html/template.ftl). The `.ftlh` extension makes
  FreeMarker HTML-escape every ${...}, so a vote title or a name can't inject markup or links.
-->
<!DOCTYPE html>
<html lang="${lang}">
<body style="font-family:sans-serif;background:#0d0d1a;color:#f0f0f0;padding:24px">
<h2 style="color:#f59e0b">${heading}</h2>
<h3 style="color:#fff">${title}</h3>
<p style="font-size:${winner?has_content?then('18px', '16px')}">${lead}</p>
<#if winner?has_content>
<p style="font-size:24px;color:#f59e0b;font-weight:bold">${winner}</p>
</#if>
<#if winnerEmail?has_content>
<p style="color:#a0a0b0">${winnerEmail}</p>
</#if>
<a href="${link}" style="display:inline-block;background:#f59e0b;color:#000;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;margin-top:16px">${button}</a>
<p style="color:#a0a0b0;font-size:12px;margin-top:32px">
  ${footer}
  <a href="${unsubscribeLink}" style="color:#a0a0b0">${unsubscribe}</a>
</p>
</body>
</html>
