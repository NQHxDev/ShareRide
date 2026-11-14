<!DOCTYPE html>
<html lang="${locale!'vi'}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${subject}</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            line-height: 1.6;
            color: #333333;
            background-color: #f4f4f4;
            margin: 0;
            padding: 0;
        }
        .email-container {
            max-width: 600px;
            margin: 20px auto;
            background-color: #ffffff;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            overflow: hidden;
        }
        .email-body {
            padding: 30px;
        }
        .button {
            display: inline-block;
            padding: 12px 24px;
            background-color: #007bff;
            color: #ffffff;
            text-decoration: none;
            border-radius: 4px;
            font-weight: 600;
        }
        .button:hover {
            background-color: #0056b3;
        }
        .verification-code {
            font-size: 32px;
            font-weight: bold;
            letter-spacing: 4px;
            color: #007bff;
            padding: 20px;
            background-color: #f8f9fa;
            border-radius: 4px;
            text-align: center;
            margin: 20px 0;
        }
    </style>
</head>
<body>
    <div class="email-container">
        <#include "header.ftl">
        <div class="email-body">
            ${content?no_esc}
        </div>
        <#include "footer.ftl">
    </div>
</body>
</html>
