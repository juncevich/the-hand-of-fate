<#import "template.ftl" as layout>
<@layout.emailLayout
  title=msg("fateIdentityProviderLinkTitle", identityProviderDisplayName)
  text=msg("fateIdentityProviderLinkText", identityProviderDisplayName, identityProviderContext.username)
  link=link
  button=msg("fateIdentityProviderLinkButton")
  expires=msg("fateLinkExpires", linkExpirationFormatter(linkExpiration))
  footer=msg("fateIdentityProviderLinkFooter")
/>
