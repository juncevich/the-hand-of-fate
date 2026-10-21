<#import "template.ftl" as layout>
<@layout.emailLayout
  title=msg("fatePasswordResetTitle")
  text=msg("fatePasswordResetText")
  link=link
  button=msg("fatePasswordResetButton")
  expires=msg("fateLinkExpires", linkExpirationFormatter(linkExpiration))
  footer=msg("fatePasswordResetFooter")
/>
