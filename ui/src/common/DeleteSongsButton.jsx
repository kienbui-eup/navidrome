import React from 'react'
import PropTypes from 'prop-types'
import { useDispatch } from 'react-redux'
import { Button, useTranslate } from 'react-admin'
import DeleteIcon from '@material-ui/icons/Delete'
import { alpha, makeStyles } from '@material-ui/core/styles'
import clsx from 'clsx'
import { openDeleteMediaDialog } from '../actions'

const useStyles = makeStyles(
  (theme) => ({
    deleteButton: {
      color: theme.palette.error.main,
      '&:hover': {
        backgroundColor: alpha(theme.palette.error.main, 0.12),
        '@media (hover: none)': {
          backgroundColor: 'transparent',
        },
      },
    },
  }),
  { name: 'NDDeleteSongsButton' },
)

export const DeleteSongsButton = ({ selectedIds, className }) => {
  const classes = useStyles()
  const dispatch = useDispatch()
  const translate = useTranslate()

  const handleClick = () => {
    dispatch(
      openDeleteMediaDialog({
        mode: 'songs',
        record: { ids: selectedIds, count: selectedIds.length },
      }),
    )
  }

  return (
    <Button
      onClick={handleClick}
      label={translate('resources.song.actions.delete')}
      className={clsx(classes.deleteButton, className)}
    >
      <DeleteIcon />
    </Button>
  )
}

DeleteSongsButton.propTypes = {
  selectedIds: PropTypes.arrayOf(PropTypes.string).isRequired,
}

export default DeleteSongsButton
