import * as React from "react";

import { cn } from "@/lib/utils";

type DrawerDirection = "top" | "bottom";

type DrawerContextValue = {
  direction: DrawerDirection;
  open: boolean;
  setOpen: (value: boolean) => void;
};

const DrawerContext = React.createContext<DrawerContextValue | null>(null);

function useDrawerContext(): DrawerContextValue {
  const context = React.useContext(DrawerContext);
  if (context === null) {
    throw new Error("Drawer components must be used inside <Drawer />.");
  }
  return context;
}

type DrawerProps = {
  children: React.ReactNode;
  direction?: DrawerDirection;
  open?: boolean;
  onOpenChange?: (value: boolean) => void;
  shouldScaleBackground?: boolean;
};

const Drawer = ({
  children,
  direction = "bottom",
  open: controlledOpen,
  onOpenChange,
}: DrawerProps) => {
  const [uncontrolledOpen, setUncontrolledOpen] = React.useState(false);
  const isControlled = typeof controlledOpen === "boolean";
  const open = isControlled ? controlledOpen : uncontrolledOpen;
  const setOpen = React.useCallback(
    (value: boolean) => {
      if (!isControlled) {
        setUncontrolledOpen(value);
      }
      onOpenChange?.(value);
    },
    [isControlled, onOpenChange]
  );

  return (
    <DrawerContext.Provider value={{ direction, open, setOpen }}>
      {children}
    </DrawerContext.Provider>
  );
};
Drawer.displayName = "Drawer";

type DrawerButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement>;

const DrawerTrigger = ({ children, onClick, ...props }: DrawerButtonProps) => {
  const { setOpen } = useDrawerContext();

  return (
    <button
      type="button"
      onClick={(event) => {
        onClick?.(event);
        if (!event.defaultPrevented) {
          setOpen(true);
        }
      }}
      {...props}
    >
      {children}
    </button>
  );
};
DrawerTrigger.displayName = "DrawerTrigger";

const DrawerClose = ({ children, onClick, ...props }: DrawerButtonProps) => {
  const { setOpen } = useDrawerContext();

  return (
    <button
      type="button"
      onClick={(event) => {
        onClick?.(event);
        if (!event.defaultPrevented) {
          setOpen(false);
        }
      }}
      {...props}
    >
      {children}
    </button>
  );
};
DrawerClose.displayName = "DrawerClose";

const DrawerPortal = ({ children }: { children: React.ReactNode }) => <>{children}</>;
DrawerPortal.displayName = "DrawerPortal";

const DrawerOverlay = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement>
>(({ className, ...props }, ref) => {
  const { open } = useDrawerContext();
  if (!open) return null;
  return (
    <div
      ref={ref}
      className={cn("hidden", className)}
      {...props}
    />
  );
});
DrawerOverlay.displayName = "DrawerOverlay";

const DrawerContent = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement>
>(({ className, children, ...props }, ref) => {
  const { direction, open } = useDrawerContext();
  if (!open) {
    return null;
  }

  const directionClass = direction === "top" ? "rounded-b-xl" : "rounded-t-xl";

  return (
    <div className="animate-fade-up">
      <div
        ref={ref}
        className={cn(
          "relative z-20 mb-3 flex max-h-[92vh] flex-col border border-border/70 bg-card",
          directionClass,
          className
        )}
        {...props}
      >
        <div className="mx-auto mt-3 h-1.5 w-16 rounded-full bg-muted" />
        {children}
      </div>
    </div>
  );
});
DrawerContent.displayName = "DrawerContent";

const DrawerHeader = ({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn("grid gap-1.5 p-4 text-left", className)} {...props} />
);
DrawerHeader.displayName = "DrawerHeader";

const DrawerFooter = ({
  className,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn("mt-auto flex flex-col gap-2 p-4", className)} {...props} />
);
DrawerFooter.displayName = "DrawerFooter";

const DrawerTitle = React.forwardRef<
  HTMLHeadingElement,
  React.HTMLAttributes<HTMLHeadingElement>
>(({ className, ...props }, ref) => (
  <h2
    ref={ref}
    className={cn("text-lg font-semibold leading-none tracking-tight", className)}
    {...props}
  />
));
DrawerTitle.displayName = "DrawerTitle";

const DrawerDescription = React.forwardRef<
  HTMLParagraphElement,
  React.HTMLAttributes<HTMLParagraphElement>
>(({ className, ...props }, ref) => (
  <p
    ref={ref}
    className={cn("text-sm text-muted-foreground", className)}
    {...props}
  />
));
DrawerDescription.displayName = "DrawerDescription";

export {
  Drawer,
  DrawerClose,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerOverlay,
  DrawerPortal,
  DrawerTitle,
  DrawerTrigger,
};
