#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import os
import sys
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox

try:
    from PIL import Image, ImageTk
except ImportError:
    print("缺少 Pillow，请先执行: pip install pillow")
    sys.exit(1)


CROP_SIZE = 100
WINDOW_MARGIN = 120


class AvatarCropper:
    def __init__(self, root: tk.Tk) -> None:
        self.root = root
        self.root.title("头像裁剪 100x100")
        self.root.configure(bg="#f5f1e8")

        self.image_path = self.choose_image()
        if not self.image_path:
            self.root.destroy()
            return

        self.original_image = Image.open(self.image_path).convert("RGBA")
        self.scale = self.calculate_scale(
            self.original_image.width,
            self.original_image.height,
        )
        self.display_width = int(self.original_image.width * self.scale)
        self.display_height = int(self.original_image.height * self.scale)
        self.display_image = self.original_image.resize(
            (self.display_width, self.display_height),
            Image.Resampling.LANCZOS,
        )
        self.tk_image = ImageTk.PhotoImage(self.display_image)

        self.crop_box_size = max(1, round(CROP_SIZE * self.scale))
        self.crop_x = max(0, (self.display_width - self.crop_box_size) // 2)
        self.crop_y = max(0, (self.display_height - self.crop_box_size) // 2)

        self.drag_offset_x = 0
        self.drag_offset_y = 0
        self.dragging = False

        self.build_ui()
        self.draw_crop_box()

    def choose_image(self) -> str:
        return filedialog.askopenfilename(
            title="选择要裁剪的图片",
            filetypes=[
                ("图片文件", "*.png *.jpg *.jpeg *.webp *.bmp"),
                ("所有文件", "*.*"),
            ],
        )

    def calculate_scale(self, width: int, height: int) -> float:
        screen_width = self.root.winfo_screenwidth()
        screen_height = self.root.winfo_screenheight()
        max_width = max(300, screen_width - WINDOW_MARGIN)
        max_height = max(300, screen_height - WINDOW_MARGIN - 80)
        return min(max_width / width, max_height / height, 1.0)

    def build_ui(self) -> None:
        info_text = (
            "拖动红框选择 100x100 区域，按 Enter 或 S 保存，按 Esc 退出"
        )
        tk.Label(
            self.root,
            text=info_text,
            bg="#f5f1e8",
            fg="#5b4a2d",
            font=("Microsoft YaHei", 11),
            pady=10,
        ).pack()

        self.canvas = tk.Canvas(
            self.root,
            width=self.display_width,
            height=self.display_height,
            bg="#1f1f1f",
            highlightthickness=0,
            cursor="fleur",
        )
        self.canvas.pack(padx=12, pady=(0, 12))
        self.canvas.create_image(0, 0, anchor=tk.NW, image=self.tk_image)

        self.status_var = tk.StringVar()
        self.status_label = tk.Label(
            self.root,
            textvariable=self.status_var,
            bg="#f5f1e8",
            fg="#7a6747",
            font=("Microsoft YaHei", 10),
            pady=4,
        )
        self.status_label.pack()

        button_row = tk.Frame(self.root, bg="#f5f1e8")
        button_row.pack(pady=(0, 12))

        tk.Button(
            button_row,
            text="保存裁图",
            command=self.save_crop,
            font=("Microsoft YaHei", 10),
            padx=18,
            pady=4,
        ).pack(side=tk.LEFT, padx=(0, 8))

        tk.Button(
            button_row,
            text="退出",
            command=self.root.destroy,
            font=("Microsoft YaHei", 10),
            padx=18,
            pady=4,
        ).pack(side=tk.LEFT)

        self.update_status()

        self.rect_id = None
        self.text_bg_id = None
        self.text_id = None

        self.canvas.bind("<Button-1>", self.on_mouse_down)
        self.canvas.bind("<B1-Motion>", self.on_mouse_drag)
        self.canvas.bind("<ButtonRelease-1>", self.on_mouse_up)

        self.root.bind_all("<Return>", self.save_crop)
        self.root.bind_all("<s>", self.save_crop)
        self.root.bind_all("<S>", self.save_crop)
        self.root.bind_all("<Escape>", lambda _event: self.root.destroy())
        self.canvas.focus_set()
        self.root.after(100, self.canvas.focus_force)

    def draw_crop_box(self) -> None:
        x1 = self.crop_x
        y1 = self.crop_y
        x2 = self.crop_x + self.crop_box_size
        y2 = self.crop_y + self.crop_box_size

        if self.rect_id is None:
            self.rect_id = self.canvas.create_rectangle(
                x1,
                y1,
                x2,
                y2,
                outline="#ff5252",
                width=2,
            )
            self.text_bg_id = self.canvas.create_rectangle(
                x1,
                max(0, y1 - 24),
                x1 + 92,
                y1,
                fill="#ff5252",
                outline="",
            )
            self.text_id = self.canvas.create_text(
                x1 + 46,
                max(12, y1 - 12),
                text="100 x 100",
                fill="white",
                font=("Microsoft YaHei", 9, "bold"),
            )
        else:
            self.canvas.coords(self.rect_id, x1, y1, x2, y2)
            self.canvas.coords(
                self.text_bg_id,
                x1,
                max(0, y1 - 24),
                x1 + 92,
                y1,
            )
            self.canvas.coords(
                self.text_id,
                x1 + 46,
                max(12, y1 - 12),
            )

        self.update_status()

    def on_mouse_down(self, event: tk.Event) -> None:
        if (
            self.crop_x <= event.x <= self.crop_x + self.crop_box_size
            and self.crop_y <= event.y <= self.crop_y + self.crop_box_size
        ):
            self.dragging = True
            self.drag_offset_x = event.x - self.crop_x
            self.drag_offset_y = event.y - self.crop_y
        else:
            self.move_crop_box_to(
                event.x - self.crop_box_size // 2,
                event.y - self.crop_box_size // 2,
            )

    def on_mouse_drag(self, event: tk.Event) -> None:
        if not self.dragging:
            return
        self.move_crop_box_to(
            event.x - self.drag_offset_x,
            event.y - self.drag_offset_y,
        )

    def on_mouse_up(self, _event: tk.Event) -> None:
        self.dragging = False

    def move_crop_box_to(self, x: int, y: int) -> None:
        max_x = self.display_width - self.crop_box_size
        max_y = self.display_height - self.crop_box_size
        self.crop_x = min(max(0, x), max_x)
        self.crop_y = min(max(0, y), max_y)
        self.draw_crop_box()

    def get_original_crop_box(self) -> tuple[int, int, int, int]:
        left = round(self.crop_x / self.scale)
        top = round(self.crop_y / self.scale)
        right = left + CROP_SIZE
        bottom = top + CROP_SIZE

        if right > self.original_image.width:
            right = self.original_image.width
            left = right - CROP_SIZE
        if bottom > self.original_image.height:
            bottom = self.original_image.height
            top = bottom - CROP_SIZE

        return left, top, right, bottom

    def update_status(self) -> None:
        left, top, right, bottom = self.get_original_crop_box()
        self.status_var.set(
            f"原图坐标: ({left}, {top}) - ({right}, {bottom})"
        )

    def save_crop(self, _event: tk.Event | None = None) -> None:
        left, top, right, bottom = self.get_original_crop_box()
        cropped = self.original_image.crop((left, top, right, bottom))

        source = Path(self.image_path)
        output_path = source.with_name(f"{source.stem}_avatar_100x100.png")
        cropped.save(output_path)

        messagebox.showinfo("保存成功", f"已保存到:\n{output_path}")


def main() -> None:
    root = tk.Tk()
    app = AvatarCropper(root)
    if getattr(app, "image_path", None):
        root.mainloop()


if __name__ == "__main__":
    main()
