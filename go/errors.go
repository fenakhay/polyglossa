package polyglossa

import (
	"errors"
	"fmt"
)

// Errors a lookup or a read returns, wrapped with the code that was looked up or in a [ParseError].
var (
	ErrUnknownLanguage = errors.New("polyglossa: unknown language")
	ErrUnknownFamily   = errors.New("polyglossa: unknown family")
	ErrUnknownScript   = errors.New("polyglossa: unknown script")
	ErrNotACatalog     = errors.New("polyglossa: not a catalog polyglossa wrote")
)

// ParseError is a line of a catalog that could not be read. It wraps [ErrNotACatalog].
type ParseError struct {
	// Line is the line, counted from 1.
	Line int
	// Reason is what is wrong with it.
	Reason string
}

// Error says which line could not be read, and why.
func (e *ParseError) Error() string {
	return fmt.Sprintf("polyglossa: not a catalog polyglossa wrote: line %d: %s", e.Line, e.Reason)
}

// Unwrap is [ErrNotACatalog], so that errors.Is(err, ErrNotACatalog) holds.
func (e *ParseError) Unwrap() error { return ErrNotACatalog }

func unknown(sentinel error, code string) error {
	return fmt.Errorf("%w %q", sentinel, code)
}
