<h1>${welcomeTitle}</h1>
<p>${greeting}</p>
<p>${welcomeMessage}</p>

<div style="margin: 30px 0; text-align: center;">
    <#if dashboardUrl??>
        <a href="${dashboardUrl}" style="display: inline-block; padding: 12px 32px; background-color: #007bff; color: #ffffff; text-decoration: none; border-radius: 4px; font-weight: 600; margin-right: 10px;">
            ${ctaDashboard}
        </a>
    </#if>
    <#if supportUrl??>
        <a href="${supportUrl}" style="display: inline-block; padding: 12px 32px; background-color: #28a745; color: #ffffff; text-decoration: none; border-radius: 4px; font-weight: 600;">
            ${ctaSupport}
        </a>
    </#if>
</div>

<p style="margin-top: 40px;">${regards}<br>${teamName}</p>
