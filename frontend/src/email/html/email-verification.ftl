<#import "template.ftl" as layout>
<@layout.emailLayout
  title=msg("fateEmailVerificationTitle")
  text=msg("fateEmailVerificationText")
  link=link
  button=msg("fateEmailVerificationButton")
  expires=msg("fateLinkExpires", linkExpirationFormatter(linkExpiration))
  footer=msg("fateEmailVerificationFooter")
/>
