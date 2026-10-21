<#outputformat "plainText">
<#assign requiredActionsText><#if requiredActions??><#list requiredActions><#items as reqActionItem>${msg("requiredAction.${reqActionItem}")}<#sep>, </#sep></#items></#list></#if></#assign>
</#outputformat>
<#import "template.ftl" as layout>
<@layout.emailLayout
  title=msg("fateExecuteActionsTitle")
  text=msg("fateExecuteActionsText", requiredActionsText)
  link=link
  button=msg("fateExecuteActionsButton")
  expires=msg("fateLinkExpires", linkExpirationFormatter(linkExpiration))
  footer=msg("fateExecuteActionsFooter")
/>
