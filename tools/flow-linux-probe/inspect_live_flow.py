"""Read only diagnostics for the existing Flow tab; never reads cookies or prompt text."""
import asyncio
import json

from playwright.async_api import async_playwright


async def main():
    async with async_playwright() as playwright:
        browser = await playwright.chromium.connect_over_cdp("http://127.0.0.1:9229", timeout=10_000)
        for context in browser.contexts:
            for page in context.pages:
                if not page.url.startswith("https://flow.google.com/"):
                    continue
                controls = await page.evaluate("""() => [...document.querySelectorAll(
                    'button, textarea, [contenteditable="true"], input'
                )].map(element => ({
                    tag: element.tagName,
                    aria: element.getAttribute('aria-label'),
                    title: element.getAttribute('title'),
                    placeholder: element.getAttribute('placeholder'),
                    type: element.getAttribute('type'),
                    disabled: element.disabled === true,
                    contenteditable: element.getAttribute('contenteditable'),
                    text: element.tagName === 'BUTTON' ? element.innerText.trim().slice(0, 120) : ''
                })).filter(control => control.aria || control.title || control.placeholder || control.text)""")
                print(json.dumps({"url": page.url, "title": await page.title(), "controls": controls}, ensure_ascii=False))


if __name__ == "__main__":
    asyncio.run(main())
