<#--
  Layout of every HTML email, styled like the app's own (backend EmailService): dark page, gold
  heading and button. Our templates pass the parts; the base theme's ones pass nested HTML.
  Values are escaped by FreeMarker, so user-supplied text (a name) can't inject markup.
-->
<#macro emailLayout title="" text="" link="" button="" expires="" footer="">
<!DOCTYPE html>
<html lang="${locale.language}" dir="${(ltr)?then('ltr','rtl')}">
<body style="font-family:sans-serif;background:#0d0d1a;color:#f0f0f0;padding:24px">
<h2 style="color:#f59e0b">&#10022; The Hand of Fate</h2>
<#if title?has_content>
<h3 style="color:#fff">${title}</h3>
<p>${text}</p>
<a href="${link}" style="display:inline-block;background:#f59e0b;color:#000;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;margin-top:16px">${button}</a>
<p style="color:#a0a0b0;margin-top:24px">${expires}</p>
<p style="color:#a0a0b0">${footer}</p>
<#else>
<#nested>
</#if>
</body>
</html>
</#macro>
