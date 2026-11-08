# When GPT-6 Meets Ancient Chinese Medicine

What happens when frontier AI meets a medical tradition built over centuries? For Wang Dou, the answer became **TCM 3D**: an open-source human model that lets people explore acupuncture points, move instruments across the skin, and demonstrate different techniques directly in a browser.

Developed with GPT-6-based Codex, the project turns a familiar acupuncture chart into an interactive space. Rotate the body, zoom into the scalp or sole, select a point, and watch how different tools make contact. On phones, the canvas fills the available viewport, with teaching controls that expand when needed.

## Where the Body Comes From

The foundation is **[BodyParts3D 4.0](https://dbarchive.biosciencedbc.jp/en/bodyparts3d/desc.html)**, an adult male reference anatomy dataset from The Database Center for Life Science. Its 2,234 source meshes are based on TARO MRI data and anatomical illustration refinements.

The browser viewer builds on **[Human Atlas by ashemag](https://github.com/ashemag/human-atlas)**, which supplies the React and Three.js foundation and browser-ready geometry. TCM 3D adds the Chinese teaching interface, acupuncture overlays, meridian lines, instruments, and skin-contact effects. Upstream Git history and attribution are preserved: application code uses MIT, while BodyParts3D data uses CC BY 4.0.

## What You Can Explore

The model includes **362 named points across fourteen meridians**, represented by **670 bilateral and midline surface markers**. Colored, segmented meridian lines help learners follow point sequences. The intraoral point GV28 appears in the catalogue without an external skin marker.

Six tools provide different visual demonstrations: **acupuncture needle, moxibustion, bian stone, finger pressure, gua sha, and cupping**. Users can move them along the skin and observe pressing, rebound, scraping traces, warming regions, and suction-style deformation. In point-selection mode, tools moving close to a point can snap to its anchor and update the selected point's details.

Pain examples connect headache, neck pain, low back pain, and knee pain with selected local or distant points. The original pain marker stays visible as the tool moves, making the relationship easier to explain.

## How Human Feedback Shaped the Build

Wang Dou set the direction and tested each iteration. GPT-6-based Codex helped implement features, investigate problems, and verify changes. The working method was concrete: start with traceable anatomy, attach markers to the skin, test the interaction, and revise what looked or behaved incorrectly.

“Can the tools move?” led to direct dragging. A dark circle over Yongquan turned out to be the display pedestal blocking the sole. Missing crown points were hidden beneath a separate hair mesh; hiding that layer exposed the scalp markers. These are observable engineering decisions from the development process.

GPT-6 participates in development; the finished interface runs without an AI API key.

## Help Improve It

TCM 3D is an **educational engineering preview awaiting professional review**. Point coordinates remain unreviewed, meridian lines are surface illustrations, and skin effects are procedural animations rather than calibrated treatment guidance or predictions of pain relief.

We welcome teachers, clinicians, developers, designers, and learners to help review point locations, improve interaction, and report issues.

**[Try TCM 3D](https://tcm.jic.io/3d/) · [Contribute on GitHub](https://github.com/JICio/tcm-3d) · [Join our Discord](https://discord.gg/jic)**
